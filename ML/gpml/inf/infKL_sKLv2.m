function [post nlZ dnlZ] = infKL_sKLv2(hyp, mean, cov, lik, x, y)
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

lik_name = func2str(lik{1});

snu2=hyp.snu2;
x_batch=x;
y_batch=y;
n_batch=size(x_batch,1);

% GP prior
K_batch = feval(cov{:}, hyp.cov, x_batch);                  % evaluate the covariance matrix
m_batch = feval(mean{:}, hyp.mean, x_batch);                      % evaluate the mean vector
K_batch=snu2*eye(n_batch)+K_batch;

%the size of mini batch= n_batch * mini_batch_rate
mini_batch_size=hyp.mini_batch_size;
assert (mini_batch_size>0)
mini_batch_num=ceil(n_batch/mini_batch_size);
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%init value
m_u_batch=hyp.init_m;
V_u_batch=hyp.init_V;
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
if isfield(hyp,'momentum')
	assert( ~isfield(hyp,'adadelta') )
	momentum_m_u_batch=zeros(n_batch,1);
	momentum_V_u_batch=zeros(n_batch,n_batch);
end

if isfield(hyp,'adadelta')
	assert( ~isfield(hyp,'momentum') )
	epsilon=1e-8;
	g_acc_m_u_batch=zeros(n_batch,1);
	g_delta_acc_m_u_batch=zeros(n_batch,1);
	g_acc_V_u_batch=zeros(n_batch,n_batch);
	g_delta_acc_V_u_batch=zeros(n_batch,n_batch);
end

iter = 0;
pass=0;
max_pass=hyp.max_pass;
while pass<max_pass
	index=randperm(n_batch);
	offset=0;
	mini_batch_counter=0;
	while mini_batch_counter<mini_batch_num
		iter = iter + 1;

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
		weight=double(n_batch)/size(x,1);

		alpha_batch=K_batch\(m_u_batch-m_batch);
		post_m_batch=m_u_batch;
		post_v_batch=diag(V_u_batch);

		if hyp.stochastic_approx==1
			[ll, gf, gv] = sampling_E(y, post_m_batch(idx), post_v_batch(idx), lik, hyp.sample_size, hyp.lik);
		else
			switch lik_name
			case {'laplace','likLaplace','poisson','bernoulli_logit','likLogistic'}
				[ll, gf, gv] = E_log_p(lik_name, y, post_m_batch(idx), post_v_batch(idx), hyp.lik);
			otherwise	 
				[ll,gf,g2f,gv] = likKL(post_v_batch(idx), lik, hyp.lik, y, post_m_batch(idx));
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

		g_rate=hyp.learning_rate/(iter).^(hyp.power);
		g_m_u_batch=alpha_batch-df_batch;

		W_batch=-2.0 .* dv_batch; %W_batch=- 2.0*dv_batch
		%g_V_u_batch= 0.5 *( -V_u_batch\eye(n_batch) + K_batch\eye(n_batch) +diag(W_batch));
		sW_batch=sqrt(abs(W_batch)).*sign(W_batch);
		L_batch = chol(eye(n_batch)+sW_batch*sW_batch'.*K_batch);
		T_batch = L_batch'\(repmat(sW_batch,1,n_batch).*K_batch); %T  = L'\(sW*K);
		A_batch = K_batch - T_batch'*T_batch; %A_batch=inv( inv(K_batch) + W_batch )
		g_V_u_batch=0.5*(A_batch\eye(n_batch) - V_u_batch\eye(n_batch)); %0.5*( inv(A_batch) - inv(V_u_batch) )

		if isfield(hyp,'momentum')
			momentum_m_u_batch=hyp.momentum .* momentum_m_u_batch-g_rate .* g_m_u_batch;
			m_u_batch=m_u_batch + momentum_m_u_batch;

			momentum_V_u_batch=hyp.momentum .* momentum_V_u_batch - g_rate .* g_V_u_batch;
		V_u_batch=V_u_batch + momentum_V_u_batch;
		end

		if isfield(hyp,'adadelta')
			decay_factor=hyp.decay_factor;
			m_u_scale=decay_factor .* g_acc_m_u_batch + (1.0-decay_factor) .* (g_m_u_batch.^2);
			g_acc_m_u_batch=m_u_scale;
			g_m_u_batch = (hyp.learning_rate .* g_m_u_batch .* sqrt(g_delta_acc_m_u_batch + epsilon) ./ sqrt(m_u_scale+epsilon) );
			g_delta_acc_m_u_batch=decay_factor .* g_delta_acc_m_u_batch + (1.0-decay_factor) .* (g_m_u_batch.^2);
			m_u_batch=m_u_batch-g_m_u_batch;

			V_u_scale=decay_factor .* g_acc_V_u_batch + (1.0-decay_factor) .* (g_V_u_batch.^2);
			g_acc_V_u_batch=V_u_scale;
			g_V_u_batch = (hyp.learning_rate .* g_V_u_batch .* sqrt(g_delta_acc_V_u_batch + epsilon) ./ sqrt(V_u_scale+epsilon) );
			g_delta_acc_V_u_batch=decay_factor .* g_delta_acc_V_u_batch + (1.0-decay_factor) .* (g_V_u_batch.^2);
			V_u_batch=V_u_batch-g_V_u_batch;
		end

		if ~isfield(hyp,'adadelta') && ~isfield(hyp,'momentum')
			m_u_batch=m_u_batch-g_rate .*g_m_u_batch;
			V_u_batch=V_u_batch-g_rate.*g_V_u_batch;
		end

		post_v_batch=abs(diag(V_u_batch));
		V_u_batch=V_u_batch-diag(diag(V_u_batch))+diag(post_v_batch);
		%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

		mini_batch_counter=mini_batch_counter+1;

		if isfield(hyp,'save_iter') && hyp.save_iter==1
			global cache_nlz_iter
			global cache_iter

			post_m_batch=m_u_batch;
			alpha_batch=K_batch\(m_u_batch-m_batch);
			post_v_batch=diag(V_u_batch);
			switch lik_name
			case {'laplace','likLaplace','poisson','bernoulli_logit','likLogistic'}
				[ll_iter, df_batch, dv_batch] = E_log_p(lik_name, y_batch, post_m_batch, post_v_batch, hyp.lik);
			otherwise	 
				[ll_iter,df_batch,d2f_batch,dv_batch] = likKL(post_v_batch, lik, hyp.lik, y_batch, post_m_batch);
			end
			W_batch=-2.0*dv_batch;
			sW_batch=sqrt(abs(W_batch)).*sign(W_batch);
			nlZ_batch2=batch_nlz_fullv2(lik, hyp, sW_batch, K_batch, m_batch, alpha_batch, post_m_batch, y_batch);

			cache_iter=[cache_iter; iter];
			cache_nlz_iter=[cache_nlz_iter; nlZ_batch2];
		end

	end
	pass=pass+1;

	%display nlz
	post_m_batch=m_u_batch;
	alpha_batch=K_batch\(m_u_batch-m_batch);
	post_v_batch=diag(V_u_batch);
	switch lik_name
	case {'laplace','likLaplace','poisson','bernoulli_logit','likLogistic'}
		[ll_iter, df_batch, dv_batch] = E_log_p(lik_name, y_batch, post_m_batch, post_v_batch, hyp.lik);
	otherwise	 
		[ll_iter,df_batch,d2f_batch,dv_batch] = likKL(post_v_batch, lik, hyp.lik, y_batch, post_m_batch);
	end
	W_batch=-2.0*dv_batch;
	sW_batch=sqrt(abs(W_batch)).*sign(W_batch);
	nlZ_batch=batch_nlz_fullv2(lik, hyp, sW_batch, K_batch, m_batch, alpha_batch, post_m_batch, y_batch);
	fprintf('pass:%d) %.4f\n', pass, nlZ_batch);

	if hyp.is_save==1
		global cache_post;
		global cache_nlz;

		L_batch = chol(eye(n_batch)+sW_batch*sW_batch'.*K_batch);
		post.sW = sW_batch;                                           
		post.alpha = alpha_batch;
		post.L = L_batch;                                    

		cache_post=[cache_post; post];
		cache_nlz=[cache_nlz; nlZ_batch];
	end
end
L_batch = chol(eye(n_batch)+sW_batch*sW_batch'.*K_batch); %L = chol(sW*K*sW + eye(n)); 
post.sW = sW_batch;                                             % return argument
post.alpha = alpha_batch;
post.L = L_batch;                                              % L'*L=B=eye(n)+sW*K*sW

nlZ=batch_nlz_fullv2(lik, hyp, sW_batch, K_batch, m_batch, alpha_batch, post_m_batch, y_batch);
fprintf('final: %.4f\n', nlZ);

if nargout>2
  warning('to be implemented\n');
  dnlZ = NaN;
end
