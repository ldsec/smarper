function [post nlZ dnlZ] = infKL_sprox(hyp, mean, cov, lik, x, y)
% stochastic KL-Proximal Variational Gaussian Inference 

if hyp.is_cached==1
	global cache_post;
	global cache_nlz;
	global cache_idx;
	
	post=cache_post(cache_idx);
	nlZ=cache_nlz(cache_idx);
	if nargout>2
		warning('to be implemented\n');
		dnlZ = NaN;
	end
	return 
end

snu2=hyp.snu2;
x_batch=x;
y_batch=y;
n_batch=size(x_batch,1);

% GP prior
K_batch = feval(cov{:}, hyp.cov, x_batch);                  % evaluate the covariance matrix
m_batch = feval(mean{:}, hyp.mean, x_batch);                      % evaluate the mean vector
K_batch=snu2*eye(n_batch)+K_batch;

lik_name = func2str(lik{1});
mini_batch_size=hyp.mini_batch_size;
assert (mini_batch_size>0)
mini_batch_num=ceil(n_batch/mini_batch_size);
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%init value
post_m_batch=hyp.init_m;%k=0
%tW_batch = zeros(n_batch,1);%k=-1
tW_batch = zeros(n_batch,1);%k=-1
post_v_batch=diag(hyp.init_V);%k=0
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

iter = 0;
pass=0;
max_pass=hyp.max_pass;
while pass<max_pass
	index=randperm(n_batch);
	offset=0;
	mini_batch_counter=0;
	while mini_batch_counter<mini_batch_num
		iter=iter+1;

		to_idx=(mini_batch_counter+1)*mini_batch_size;
		if to_idx>n_batch
			to_idx=n_batch;
		end
		from_idx=mini_batch_counter*mini_batch_size+1;

		idx=index(from_idx:to_idx);
		x=x_batch(idx,:);
		y=y_batch(idx);

		%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
		%mini batch
		rate=hyp.learning_rate;
		weight=double(n_batch)/size(x,1);

		beta = rate;
		r = 1/(beta+1);

		if hyp.stochastic_approx==1
			[ll, gf, gv] = sampling_E(y, post_m_batch(idx), post_v_batch(idx), lik, hyp.sample_size, hyp.lik);
		else
			switch lik_name
			case {'laplace','likLaplace','poisson','bernoulli_logit','likLogistic'}
				[ll, gf, gv] = E_log_p(lik_name, y, post_m_batch(idx), post_v_batch(idx), hyp.lik);
			otherwise	 
				[ll,gf,d2f,gv] = likKL(post_v_batch(idx), lik, hyp.lik, y, post_m_batch(idx));
			end
		end

		%using unbaised weight
		gf = gf .* weight;
		gv = gv .* weight;

		%mapping the change in a mini_batch to the change in the whole batch 
		df_batch=zeros(n_batch,1);
		df_batch(idx)=gf;

		dv_batch=zeros(n_batch,1);
		dv_batch(idx)=gv;

		W_batch = -2*dv_batch;

		% pseudo observation
		pseudo_y_batch = m_batch + K_batch*df_batch - post_m_batch;

		%remove the following if-else statement if we approximate r^{k} .* tW.^{k-1} by tW.{k}
		if isfield(hyp,'exact')
			if iter==1
				post_m_batch=post_m_batch+(1-r).*pseudo_y_batch;%m^{1}
			else
				sW_batch = sqrt(r.*tW_batch);%r^k .* tW^{k-1}
				L_batch = chol(eye(n_batch)+sW_batch*sW_batch'.*K_batch); %L = chol(sW*K*sW + eye(n)); 
				post_m_batch = post_m_batch + (1-r).*(pseudo_y_batch - K_batch*(sW_batch.*( L_batch\ (L_batch'\(sW_batch.*pseudo_y_batch)))));%m^{k+1}
			end
		end

		tW_batch = r.*tW_batch + (1-r).*W_batch;%tW^{k}
		sW_batch = sqrt(abs(tW_batch)) .* sign(tW_batch);
		L_batch = chol(eye(n_batch)+sW_batch*sW_batch'.*K_batch); %L = chol(sW*K*sW + eye(n)); 

		%use this following line if we approximate r^{k} .* tW.^{k-1} by tW.{k}
		if ~isfield(hyp,'exact')
			post_m_batch = post_m_batch + (1-r).*(pseudo_y_batch - K_batch*(sW_batch.*( L_batch\ (L_batch'\(sW_batch.*pseudo_y_batch)))));%m^{k+1}
		end

		T_batch = L_batch'\(repmat(sW_batch,1,n_batch).*K_batch); %T  = L'\(sW*K);
		post_v_batch = diag(K_batch) - sum(T_batch.*T_batch,1)'; % v = diag(inv(inv(K)+diag(W))); %v^{k+1}

		%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

		mini_batch_counter=mini_batch_counter+1;

		if isfield(hyp,'save_iter') && hyp.save_iter==1
			global cache_nlz_iter
			global cache_iter

			alpha_batch=K_batch\(post_m_batch-m_batch);
			nlZ_batch2=batch_nlz_fullv2(lik, hyp, sW_batch, K_batch, m_batch, alpha_batch, post_m_batch, y_batch);
			cache_iter=[cache_iter; iter];
			cache_nlz_iter=[cache_nlz_iter; nlZ_batch2];
		end

	end
	alpha_batch=K_batch\(post_m_batch-m_batch);
	nlZ_batch=batch_nlz_fullv2(lik, hyp, sW_batch, K_batch, m_batch, alpha_batch, post_m_batch, y_batch);

	pass=pass+1;
	if isfield(hyp,'save_iter') && hyp.save_iter==1
		if pass==1
			global num_iters_at_pass;
			num_iters_at_pass=iter;
		end
		fprintf('pass:%d) at %d iter %.4f %.4f\n', pass, iter, nlZ_batch, nlZ_batch2);
	else
		fprintf('pass:%d) %.4f\n', pass, nlZ_batch);
	end

	if hyp.is_save==1
		global cache_post;
		global cache_nlz;

		post.sW = sW_batch;                                             % return argument
		post.alpha = alpha_batch;
		post.L = L_batch;      

		cache_post=[cache_post; post];
		cache_nlz=[cache_nlz; nlZ_batch];
	end
end

alpha_batch=K_batch\(post_m_batch-m_batch);
post.sW = sW_batch;                                             % return argument
post.alpha = alpha_batch;
post.L = L_batch;                                              % L'*L=B=eye(n)+sW*K*sW

nlZ=batch_nlz_fullv2(lik, hyp, sW_batch, K_batch, m_batch, alpha_batch, post_m_batch, y_batch);
fprintf('final: %.4f\n', nlZ);

if nargout>2
  warning('to be implemented\n');
  dnlZ = NaN;
end
